import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Runtime } from './runtime';

describe('Runtime', () => {
  let component: Runtime;
  let fixture: ComponentFixture<Runtime>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Runtime]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Runtime);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
