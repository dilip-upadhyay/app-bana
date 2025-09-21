import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Designer } from './designer';

describe('Designer', () => {
  let component: Designer;
  let fixture: ComponentFixture<Designer>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Designer]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Designer);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
